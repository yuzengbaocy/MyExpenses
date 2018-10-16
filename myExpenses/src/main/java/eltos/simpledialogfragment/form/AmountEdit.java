package eltos.simpledialogfragment.form;

import android.os.Parcel;

public class AmountEdit  extends FormElement<AmountEdit, AmountEditViewHolder> {
  int fractionDigits;
  protected AmountEdit(String resultKey) {
    super(resultKey);
  }

  public AmountEdit fractionDigits(int i) {
    this.fractionDigits = i;
    return this;
  }

  public static AmountEdit plain(String resultKey) {
    return new AmountEdit(resultKey);
  }

  @Override
  public AmountEditViewHolder buildViewHolder() {
    return new AmountEditViewHolder(this);
  }


  protected AmountEdit(Parcel in) {
    super(in);
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    super.writeToParcel(dest, flags);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<AmountEdit> CREATOR = new Creator<AmountEdit>() {
    @Override
    public AmountEdit createFromParcel(Parcel in) {
      return new AmountEdit(in);
    }

    @Override
    public AmountEdit[] newArray(int size) {
      return new AmountEdit[size];
    }
  };
}
